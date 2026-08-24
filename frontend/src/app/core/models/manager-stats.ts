export interface ManagerStats {
  travelCount: number;
  travelerCount: number;
  estimatedRevenue: number;
}

export interface ManagerPublicStats {
  travelCount: number;
  averageRating: number | null;
  reportCount: number;
}
