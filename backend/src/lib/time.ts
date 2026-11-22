const WIB_FORMATTER = new Intl.DateTimeFormat('id-ID', {
  timeZone: 'Asia/Jakarta',
  dateStyle: 'medium',
  timeStyle: 'short',
});

export function formatWib(date: Date): string {
  return WIB_FORMATTER.format(date) + ' WIB';
}
