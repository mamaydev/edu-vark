# Self-hosted GitHub Actions runner

The runner lives in the same LXC as the backend and PostgreSQL (LXC `10100`). This is a
deliberate constraint from the brief; the security consequences and mitigations are in
[`security.md`](security.md).

## Identity

| Property | Value |
| --- | --- |
| System user | `eduvark-runner` (no login password, system account) |
| Home | `/home/eduvark-runner` |
| Install dir | `/opt/eduvark/runner` (owner `eduvark-runner`, mode `0750`) |
| Work dir | `/opt/eduvark/runner/_work` |
| Runner name | `eduvark-lxc-10100` |
| Labels | `self-hosted`, `linux`, `x64`, `eduvark-deploy` |
| Scope | repository `mamaydev/edu-vark` only (no org runner, no runner group) |
| Service | `github-runner-eduvark.service` (systemd, system-wide) |
| Docker access | **none** — not in the `docker` group, no socket mounted |
| Privileged access | only `sudo -n /usr/local/sbin/eduvark-deploy ...` |

## Installation

```bash
# 1. user + binaries
useradd --create-home --home-dir /home/eduvark-runner --shell /bin/bash eduvark-runner
passwd -l eduvark-runner
install -d -o eduvark-runner -g eduvark-runner -m 0750 /opt/eduvark/runner
cd /opt/eduvark/runner
curl -fsSL -o actions-runner.tar.gz \
  https://github.com/actions/runner/releases/download/v2.338.2/actions-runner-linux-x64-2.338.2.tar.gz
tar xzf actions-runner.tar.gz && rm actions-runner.tar.gz
chown -R eduvark-runner:eduvark-runner /opt/eduvark/runner

# 2. registration token (expires in 1 hour, never commit it)
TOKEN=$(curl -s -X POST -H "Authorization: Bearer $GITHUB_PAT" \
  https://api.github.com/repos/mamaydev/edu-vark/actions/runners/registration-token |
  jq -r .token)

# 3. register
cd /opt/eduvark/runner
runuser -u eduvark-runner -- ./config.sh \
  --url https://github.com/mamaydev/edu-vark \
  --token "$TOKEN" \
  --name eduvark-lxc-10100 \
  --labels eduvark-deploy \
  --work /opt/eduvark/runner/_work \
  --unattended --replace
```

`/opt/eduvark` is `0755` so the runner user can traverse to `/opt/eduvark/runner`;
`/opt/eduvark/try-pam` stays `0700 root` and the two env files stay `0600 root`, so the
runner cannot read application secrets.

## systemd unit

The v2.338.2 tarball ships `bin/systemd.svc.sh.template` but not the `svc.sh` wrapper, so the
unit is installed directly:

```ini
[Unit]
Description=GitHub Actions Runner (mamaydev/edu-vark, eduvark-deploy)
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
User=eduvark-runner
Group=eduvark-runner
WorkingDirectory=/opt/eduvark/runner
Environment=HOME=/home/eduvark-runner
ExecStart=/opt/eduvark/runner/run.sh
Restart=always
RestartSec=5
KillMode=process
KillSignal=SIGTERM
TimeoutStopSec=300
NoNewPrivileges=no
PrivateTmp=yes
ProtectSystem=full

[Install]
WantedBy=multi-user.target
```

`NoNewPrivileges` stays `no` because the job escalates through the sudoers rule. That rule
is the actual privilege boundary, not systemd hardening.

```bash
systemctl daemon-reload
systemctl enable --now github-runner-eduvark.service
systemctl status github-runner-eduvark.service --no-pager
journalctl -u github-runner-eduvark.service -f
```

## Privilege model

`/etc/sudoers.d/eduvark-runner`:

```sudoers
Defaults:eduvark-runner !requiretty
eduvark-runner ALL=(root) NOPASSWD: /usr/local/sbin/eduvark-deploy verify *, \
  /usr/local/sbin/eduvark-deploy dry-run *, \
  /usr/local/sbin/eduvark-deploy deploy *, \
  /usr/local/sbin/eduvark-deploy rollback *, \
  /usr/local/sbin/eduvark-deploy status
```

Consequences:

- the runner cannot run arbitrary `docker`, `systemctl`, `psql` or shell commands as root;
- `eduvark-deploy` is root-owned (`0750`), validates the SHA format, re-fetches `origin/main`
  and refuses commits that are not ancestors of `main`;
- the workflow itself never runs repository code on the runner (no `actions/checkout` in the
  deploy job), so a malicious commit cannot execute before approval and validation.

Verified as the runner user:

```bash
runuser -u eduvark-runner -- sudo -n /usr/local/sbin/eduvark-deploy status
runuser -u eduvark-runner -- sudo -n /usr/local/sbin/eduvark-deploy verify <sha>
runuser -u eduvark-runner -- docker ps        # must fail: permission denied on socket
```

## Updating the runner

```bash
systemctl stop github-runner-eduvark.service
cd /opt/eduvark/runner
curl -fsSL -o actions-runner.tar.gz \
  https://github.com/actions/runner/releases/download/v<X.Y.Z>/actions-runner-linux-x64-<X.Y.Z>.tar.gz
tar xzf actions-runner.tar.gz && rm actions-runner.tar.gz
chown -R eduvark-runner:eduvark-runner /opt/eduvark/runner
systemctl start github-runner-eduvark.service
```

Runner settings (name, labels, work dir) are stored in `.runner` and `.credentials` and
survive binary upgrades.

## Verification

```bash
# on GitHub
curl -s -H "Authorization: Bearer $GITHUB_PAT" \
  https://api.github.com/repos/mamaydev/edu-vark/actions/runners |
  jq '.runners[] | {name, status, labels: [.labels[].name]}'
# expected: eduvark-lxc-10100, online, [self-hosted, Linux, X64, eduvark-deploy]

# on the container
systemctl is-active github-runner-eduvark.service
journalctl -u github-runner-eduvark.service -n 20 --no-pager
```

## Removal / rotation

1. `systemctl disable --now github-runner-eduvark.service`
2. Delete the runner in GitHub: Settings → Actions → Runners → `eduvark-lxc-10100` → Remove
   (or `DELETE /repos/{owner}/{repo}/actions/runners/{runner_id}`).
3. `rm -rf /opt/eduvark/runner /home/eduvark-runner` and `userdel eduvark-runner` if the
   host is being retired.

Rotate by re-running the registration with a fresh token and `--replace`; revoke the old
runner entry afterwards.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Job stays queued | Runner offline? `systemctl status`; labels must include `eduvark-deploy`; repo is public but the workflow must be dispatched from `main`. |
| `sudo: a password is required` | `/etc/sudoers.d/eduvark-runner` missing/unreadable, or the command path differs. `visudo -cf` it. |
| `Permission denied` on `/opt/eduvark/runner` | Parent dir `/opt/eduvark` must be `0755`. |
| `docker: permission denied` in a job | Expected. Jobs must use the deploy engine, never docker directly. |
| Runner offline after reboot | `systemctl is-enabled github-runner-eduvark.service`; check `journalctl` for network/TLS errors. |