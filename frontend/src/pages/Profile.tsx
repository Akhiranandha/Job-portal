import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Alert, Badge, Button, Card, Container, ListGroup, Spinner } from "react-bootstrap";
import { fetchMe } from "../api/profile";
import { extractErrorMessage } from "../api/client";
import type { UserProfile } from "../types/api";

export default function Profile() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    fetchMe()
      .then(setProfile)
      .catch((err) => setError(extractErrorMessage(err, "Could not load profile")))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <Container className="text-center">
        <Spinner animation="border" />
      </Container>
    );
  }

  if (error || !profile) {
    return (
      <Container>
        <Alert variant="danger">{error ?? "Profile unavailable."}</Alert>
      </Container>
    );
  }

  const isCandidate = profile.role === "JOB_SEEKER";

  return (
    <Container style={{ maxWidth: 800 }}>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h4 mb-0">My profile</h1>
        <Button onClick={() => navigate("/profile/edit")} variant="primary">
          Edit profile
        </Button>
      </div>

      <Card className="mb-3">
        <Card.Body>
          <h2 className="h5">Basic info</h2>
          <div className="mb-2">
            <Badge bg="secondary">{profile.role}</Badge>
          </div>
          <ListGroup variant="flush">
            <ListGroup.Item><strong>Name:</strong> {profile.firstName} {profile.lastName}</ListGroup.Item>
            <ListGroup.Item><strong>Email:</strong> {profile.email}</ListGroup.Item>
            {profile.phoneNumber && <ListGroup.Item><strong>Phone:</strong> {profile.phoneNumber}</ListGroup.Item>}
            {profile.bio && <ListGroup.Item><strong>Bio:</strong> {profile.bio}</ListGroup.Item>}
            {profile.linkedInUrl && <ListGroup.Item><strong>LinkedIn:</strong> {profile.linkedInUrl}</ListGroup.Item>}
            {profile.portfolioUrl && <ListGroup.Item><strong>Portfolio:</strong> {profile.portfolioUrl}</ListGroup.Item>}
          </ListGroup>
        </Card.Body>
      </Card>

      {isCandidate && (
        <>
          <Card className="mb-3">
            <Card.Body>
              <h2 className="h5">Skills</h2>
              {profile.skills && profile.skills.length > 0 ? (
                <div>
                  {profile.skills.map((s) => (
                    <Badge key={s} bg="info" className="me-1 mb-1">{s}</Badge>
                  ))}
                </div>
              ) : (
                <p className="text-muted mb-0">No skills added yet.</p>
              )}
            </Card.Body>
          </Card>

          <Card className="mb-3">
            <Card.Body>
              <h2 className="h5">Experience</h2>
              {profile.experience && profile.experience.length > 0 ? (
                profile.experience.map((e, i) => (
                  <div key={i} className="mb-3">
                    <div className="fw-semibold">{e.role} — {e.company}</div>
                    <div className="text-muted small">{e.startDate} → {e.endDate ?? "Present"}</div>
                    {e.description && <div className="small">{e.description}</div>}
                  </div>
                ))
              ) : (
                <p className="text-muted mb-0">No experience added yet.</p>
              )}
            </Card.Body>
          </Card>

          <Card className="mb-3">
            <Card.Body>
              <h2 className="h5">Education</h2>
              {profile.education && profile.education.length > 0 ? (
                profile.education.map((e, i) => (
                  <div key={i} className="mb-3">
                    <div className="fw-semibold">{e.degree}, {e.field}</div>
                    <div>{e.institution}</div>
                    <div className="text-muted small">{e.startYear} → {e.endYear ?? "Present"}</div>
                  </div>
                ))
              ) : (
                <p className="text-muted mb-0">No education added yet.</p>
              )}
            </Card.Body>
          </Card>

          <Card className="mb-3">
            <Card.Body>
              <h2 className="h5">Job preferences</h2>
              {profile.jobPreferences ? (
                <ListGroup variant="flush">
                  {profile.jobPreferences.locations && profile.jobPreferences.locations.length > 0 && (
                    <ListGroup.Item><strong>Locations:</strong> {profile.jobPreferences.locations.join(", ")}</ListGroup.Item>
                  )}
                  {(profile.jobPreferences.salaryMin != null || profile.jobPreferences.salaryMax != null) && (
                    <ListGroup.Item>
                      <strong>Salary:</strong> {profile.jobPreferences.salaryMin ?? "—"} – {profile.jobPreferences.salaryMax ?? "—"} {profile.jobPreferences.currency ?? "INR"}
                    </ListGroup.Item>
                  )}
                  {profile.jobPreferences.remote != null && (
                    <ListGroup.Item><strong>Remote:</strong> {profile.jobPreferences.remote ? "Yes" : "No"}</ListGroup.Item>
                  )}
                  {profile.jobPreferences.employmentTypes && profile.jobPreferences.employmentTypes.length > 0 && (
                    <ListGroup.Item><strong>Employment types:</strong> {profile.jobPreferences.employmentTypes.join(", ")}</ListGroup.Item>
                  )}
                </ListGroup>
              ) : (
                <p className="text-muted mb-0">No preferences set.</p>
              )}
            </Card.Body>
          </Card>
        </>
      )}

      {!isCandidate && (
        <Card className="mb-3">
          <Card.Body>
            <h2 className="h5">Company</h2>
            {profile.companyName ? (
              <ListGroup variant="flush">
                <ListGroup.Item><strong>Company:</strong> {profile.companyName}</ListGroup.Item>
                {profile.designation && <ListGroup.Item><strong>Designation:</strong> {profile.designation}</ListGroup.Item>}
                {profile.companyWebsite && <ListGroup.Item><strong>Website:</strong> {profile.companyWebsite}</ListGroup.Item>}
              </ListGroup>
            ) : (
              <p className="text-muted mb-0">No company info set.</p>
            )}
          </Card.Body>
        </Card>
      )}
    </Container>
  );
}
