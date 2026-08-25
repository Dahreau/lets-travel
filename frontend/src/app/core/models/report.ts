export type ReportedType = 'MANAGER' | 'TRAVELER';

export interface ReportRequest {
  reportedType: ReportedType;
  reportedId: string;
  reason: string;
}
