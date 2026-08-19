/** Converts an ISO instant (e.g. from the backend) to a value usable in an <input type="datetime-local">. */
export function toDatetimeLocalValue(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Converts an <input type="datetime-local"> value back to an ISO instant string for the backend. */
export function toIsoInstant(datetimeLocalValue: string): string {
  return new Date(datetimeLocalValue).toISOString();
}
