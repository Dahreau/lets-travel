export interface ManagerTravelStats {
  travelId: string;
  title: string;
  subscriberCount: number;
  averageRating: number | null;
  feedbackCount: number;
}

export interface ManagerStats {
  travelCount: number;
  travelerCount: number;
  estimatedRevenue: number;
  travels: ManagerTravelStats[];
}

export interface ManagerPublicTravelRating {
  travelId: string;
  title: string;
  averageRating: number | null;
  feedbackCount: number;
}

export interface ManagerPublicStats {
  travelCount: number;
  averageRating: number | null;
  reportCount: number;
  travelRatings: ManagerPublicTravelRating[];
}
