export interface Feedback {
  id: string;
  travelId: string;
  travelTitle: string;
  travelerId: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface FeedbackRequest {
  rating: number;
  comment: string;
}
