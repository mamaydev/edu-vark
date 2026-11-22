# Deployment architecture

## Scope

EduVARK runs on a single Proxmox VE host. The Android client, the Express/TypeScript API
and PostgreSQL share one LXC container (`10100`, hostname `pam`). A separate LXC (`10080`)
terminates TLS through Traefik and publishes the API to the internet through a Cloudflare
Tunnel.

This document describes what exists today, not an aspirational design.

```mermaid
flowchart LR
  subgraph internet[Internet]
    CF[Cloudflare edge<br/>pam.sequein.my.id]
    GH[GitHub<br/>mamaydev/edu-vark]
  end

  subgraph pve[Proxmox VE 9.2.26 - host mamay 100.65.29.10]
    subgraph lxc10080[LXC 10080 - ingress 192.168.0.80]
      TR[Traefik v3 :443/:80]
      CFD[cloudflared]
    end

    subgraph lxc10100[LXC 10100 - pam 192.168.0.100]
      RUN[GitHub Actions runner<br/>eduvark-runner, no docker group]
      DEP[/usr/local/sbin/eduvark-deploy<br/>root-owned]
      BE[eduvark-backend<br/>node 24, :3000]
      PG[(eduvark-postgres<br/>PostgreSQL 17)]
      VOL[(volume try-pam_eduvark_pgdata)]
      BK[/opt/eduvark/backups<br/>pg_dump -Fc/]
    end
  end

  CF --> TR
  CFD -.-> TR
  TR -->|HTTP 3000| BE
  BE -->|internal network| PG
  PG --- VOL
  DEP --> BK
  DEP -->|docker build/up| BE
  RUN -->|sudo -n, fixed command| DEP
  GH -->|workflow job, label eduvark-deploy| RUN
```

## Components

| Component | Location | Notes |
| --- | --- | --- |
| Proxmox host | `mamay`, `100.65.29.10` (Tailscale) | PVE 9.2.26, Debian 13, 4 physical cores (i5-7500T). |
| LXC `10100` | unprivileged, Ubuntu 24.04.5 | 2 vCPU, 8 GB RAM, 512 MB swap, 20 GB rootfs on `nvme-storage`, `nesting=1,keyctl=1`. |
| Docker Engine | LXC `10100` | 29.10.0, Compose v5.8.0, overlayfs, cgroup v2, `live-restore=true`, log rotation `10m/3`. No TCP socket. |
| PostgreSQL | container `eduvark-postgres` | `postgres:17-alpine`, volume `try-pam_eduvark_pgdata`, no published port, healthcheck `pg_isready`. |
| Backend | container `eduvark-backend` | image `eduvark-backend:<sha12>`, `node dist/server.js`, non-root `node` user, published `192.168.0.100:3000`. |
| Docker network | `try-pam_eduvark_internal` | internal bridge; only the backend reaches PostgreSQL. |
| Ingress | LXC `10080`, `192.168.0.80` | Traefik v3 + cloudflared. Route `pam.sequein.my.id` → `http://192.168.0.100:3000`. Managed outside this repo. |
| Runner | LXC `10100`, `/opt/eduvark/runner` | repo-scoped, `eduvark-runner` user, systemd `github-runner-eduvark.service`, label `eduvark-deploy`. |
| Deploy engine | `/usr/local/sbin/eduvark-deploy` | root-owned 0750, only privileged command the runner may run through sudo. |
| Backups | `/opt/eduvark/backups` | logical dumps (`pg_dump -Fc`), 14 kept, 03:30 timer + one backup per deployment. |
| Deployment state | `/var/lib/eduvark/state.json` | last release, previous image, backup path, migrations, result. |

## Network and data flow

1. Android client calls `https://pam.sequein.my.id/api/v1/...`.
2. Cloudflare terminates public TLS and forwards to the tunnel.
3. `cloudflared` in LXC `10080` forwards to Traefik.
4. Traefik proxies to `http://192.168.0.100:3000` over `vmbr0`.
5. The backend talks to `eduvark-postgres:5432` on the internal Docker network only.
6. Object storage (Cloudflare R2) is used by the backend for file uploads when configured.

No service publishes PostgreSQL. The backend binds only to `192.168.0.100`, never to
`0.0.0.0`, so it is reachable from the LAN and the ingress LXC, not from the internet
directly.

## Trust boundaries

| Boundary | Trust |
| --- | --- |
| GitHub repository `main` | Semi-trusted. Only reviewed/CI-green commits may be released; the environment requires manual approval. |
| Self-hosted runner | Untrusted by design (it executes workflow YAML). It runs as `eduvark-runner`, has no docker access, and can only invoke the root-owned deploy engine via a narrow sudoers rule. |
| Workflow `deploy-homelab.yml` | Trusted entry point, but gated: manual dispatch + environment approval + commit must be an ancestor of `main` with a green CI run. |
| Deploy engine | Root. Re-validates the commit (format, ancestry, clean checkout) before building anything. |
| Secret files | Root-only `0600`: `/opt/eduvark/try-pam/.env` and `backend/.env.production`. The runner user cannot read them. |
| Ingress LXC | Managed separately; changes require owner approval. |

## Why one container for runner + app + database

The brief fixes this co-location. The trade-offs are documented in
[`security.md`](security.md); the main mitigations are:

- runner is not root and not in the `docker` group;
- privileged work is centralised in one auditable, root-owned script;
- CD is manual and environment-protected, so a compromised workflow cannot deploy silently;
- deployment records and backups make a bad release recoverable.