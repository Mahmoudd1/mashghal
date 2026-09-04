/**
 * Formats a Date as a local calendar date (yyyy-MM-dd).
 *
 * Never `toISOString()`: that converts to UTC first, which shifts the date by a
 * day for anyone east or west of Greenwich at the wrong hour.
 */
export function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
