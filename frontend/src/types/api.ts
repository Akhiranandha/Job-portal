// Mirrors the userservice + authservice DTOs.

export type Role = "JOB_SEEKER" | "RECRUITER" | "ADMIN";

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface ErrorResponse {
  success: false;
  message: string;
  error: string;
  fieldErrors?: Record<string, string>;
  timestamp: string;
}

export interface LoginResponse {
  token: string;
  type: string;
  userId: string;
  email: string;
  role: Role;
  expiresAt: string;
}

export interface ExperienceEntry {
  company: string;
  role: string;
  startDate: string;
  endDate?: string | null;
  description?: string;
}

export interface EducationEntry {
  institution: string;
  degree: string;
  field: string;
  startYear: number;
  endYear?: number | null;
}

export interface JobPreferences {
  locations?: string[];
  salaryMin?: number;
  salaryMax?: number;
  currency?: string;
  remote?: boolean;
  employmentTypes?: string[];
}

export interface UserProfile {
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  dateOfBirth?: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  postalCode?: string;
  bio?: string;
  role: Role;
  isActive: boolean;
  isEmailVerified: boolean;
  createdAt: string;
  updatedAt: string;
  linkedInUrl?: string;
  portfolioUrl?: string;
  skills?: string[];
  experience?: ExperienceEntry[];
  education?: EducationEntry[];
  jobPreferences?: JobPreferences;
  companyName?: string;
  designation?: string;
  companyWebsite?: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role: Role;
  phoneNumber?: string;
}

export interface BasicProfileUpdate {
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  dateOfBirth?: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  postalCode?: string;
  bio?: string;
  linkedInUrl?: string;
  portfolioUrl?: string;
}

export interface RecruiterProfileRequest {
  companyName: string;
  designation?: string;
  companyWebsite?: string;
}

export interface PasswordUpdateRequest {
  currentPassword: string;
  newPassword: string;
}
