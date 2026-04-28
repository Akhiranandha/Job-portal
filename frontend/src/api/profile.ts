import { api } from "./client";
import type {
  ApiResponse,
  BasicProfileUpdate,
  EducationEntry,
  ExperienceEntry,
  JobPreferences,
  RecruiterProfileRequest,
  UserProfile,
} from "../types/api";

export async function fetchMe(): Promise<UserProfile> {
  const r = await api.get<ApiResponse<UserProfile>>("/api/users/me");
  return r.data.data;
}

export async function updateBasicProfile(payload: BasicProfileUpdate): Promise<UserProfile> {
  const r = await api.put<ApiResponse<UserProfile>>("/api/users/me", payload);
  return r.data.data;
}

export async function updateSkills(skills: string[]): Promise<UserProfile> {
  const r = await api.put<ApiResponse<UserProfile>>("/api/users/me/skills", { skills });
  return r.data.data;
}

export async function updateExperience(experience: ExperienceEntry[]): Promise<UserProfile> {
  const r = await api.put<ApiResponse<UserProfile>>("/api/users/me/experience", { experience });
  return r.data.data;
}

export async function updateEducation(education: EducationEntry[]): Promise<UserProfile> {
  const r = await api.put<ApiResponse<UserProfile>>("/api/users/me/education", { education });
  return r.data.data;
}

export async function updatePreferences(prefs: JobPreferences): Promise<UserProfile> {
  const r = await api.put<ApiResponse<UserProfile>>("/api/users/me/preferences", prefs);
  return r.data.data;
}

export async function updateRecruiterProfile(payload: RecruiterProfileRequest): Promise<UserProfile> {
  const r = await api.put<ApiResponse<UserProfile>>("/api/users/me/recruiter", payload);
  return r.data.data;
}

export async function deleteAccount(): Promise<void> {
  await api.delete("/api/users/me");
}
