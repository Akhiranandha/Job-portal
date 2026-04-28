import { useEffect, useState } from "react";
import { Alert, Container, Spinner, Tab, Tabs } from "react-bootstrap";
import { useNavigate } from "react-router-dom";
import { fetchMe } from "../api/profile";
import { extractErrorMessage } from "../api/client";
import type { UserProfile } from "../types/api";
import BasicInfoTab from "./tabs/BasicInfoTab";
import SkillsTab from "./tabs/SkillsTab";
import ExperienceTab from "./tabs/ExperienceTab";
import EducationTab from "./tabs/EducationTab";
import PreferencesTab from "./tabs/PreferencesTab";
import CompanyTab from "./tabs/CompanyTab";

export default function ProfileEdit() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState("basic");
  const navigate = useNavigate();

  useEffect(() => {
    fetchMe()
      .then(setProfile)
      .catch((err) => setError(extractErrorMessage(err, "Could not load profile")));
  }, []);

  if (error) {
    return (
      <Container>
        <Alert variant="danger">{error}</Alert>
      </Container>
    );
  }

  if (!profile) {
    return (
      <Container className="text-center">
        <Spinner animation="border" />
      </Container>
    );
  }

  const isCandidate = profile.role === "JOB_SEEKER" || profile.role === "ADMIN";
  const isRecruiter = profile.role === "RECRUITER" || profile.role === "ADMIN";

  return (
    <Container style={{ maxWidth: 900 }}>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h4 mb-0">Edit profile</h1>
        <button className="btn btn-link" onClick={() => navigate("/profile")}>
          Back to profile
        </button>
      </div>

      <Tabs
        id="profile-edit-tabs"
        activeKey={activeTab}
        onSelect={(k) => k && setActiveTab(k)}
        className="mb-3"
      >
        <Tab eventKey="basic" title="Basic info">
          <BasicInfoTab profile={profile} onSaved={setProfile} />
        </Tab>

        {isCandidate && (
          <Tab eventKey="skills" title="Skills">
            <SkillsTab profile={profile} onSaved={setProfile} />
          </Tab>
        )}
        {isCandidate && (
          <Tab eventKey="experience" title="Experience">
            <ExperienceTab profile={profile} onSaved={setProfile} />
          </Tab>
        )}
        {isCandidate && (
          <Tab eventKey="education" title="Education">
            <EducationTab profile={profile} onSaved={setProfile} />
          </Tab>
        )}
        {isCandidate && (
          <Tab eventKey="preferences" title="Job preferences">
            <PreferencesTab profile={profile} onSaved={setProfile} />
          </Tab>
        )}

        {isRecruiter && (
          <Tab eventKey="company" title="Company">
            <CompanyTab profile={profile} onSaved={setProfile} />
          </Tab>
        )}
      </Tabs>
    </Container>
  );
}
