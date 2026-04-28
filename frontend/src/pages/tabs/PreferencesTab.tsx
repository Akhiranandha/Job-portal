import { useEffect, useRef, useState } from "react";
import { Alert, Button, Form, Spinner } from "react-bootstrap";
import { updatePreferences } from "../../api/profile";
import { extractErrorMessage } from "../../api/client";
import type { JobPreferences, UserProfile } from "../../types/api";

interface Props {
  profile: UserProfile;
  onSaved: (next: UserProfile) => void;
}

const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERN"] as const;

export default function PreferencesTab({ profile, onSaved }: Props) {
  const [prefs, setPrefs] = useState<JobPreferences>(profile.jobPreferences ?? {});
  const [locationsDraft, setLocationsDraft] = useState((profile.jobPreferences?.locations ?? []).join(", "));
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saveOk, setSaveOk] = useState(false);
  const initial = useRef<string>(JSON.stringify(profile.jobPreferences ?? {}));

  useEffect(() => {
    initial.current = JSON.stringify(profile.jobPreferences ?? {});
    setPrefs(profile.jobPreferences ?? {});
    setLocationsDraft((profile.jobPreferences?.locations ?? []).join(", "));
  }, [profile.jobPreferences]);

  const isDirty = JSON.stringify(prefs) !== initial.current ||
    locationsDraft !== (profile.jobPreferences?.locations ?? []).join(", ");

  function toggleEmploymentType(t: string) {
    const current = prefs.employmentTypes ?? [];
    const next = current.includes(t) ? current.filter((x) => x !== t) : [...current, t];
    setPrefs({ ...prefs, employmentTypes: next });
  }

  async function save() {
    setServerError(null);
    setSaveOk(false);
    setSaving(true);

    const locations = locationsDraft
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);

    const payload: JobPreferences = {
      ...prefs,
      locations,
      currency: prefs.currency || "INR",
    };

    try {
      const next = await updatePreferences(payload);
      onSaved(next);
      initial.current = JSON.stringify(next.jobPreferences ?? {});
      setSaveOk(true);
    } catch (err) {
      setServerError(extractErrorMessage(err, "Save failed"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      {serverError && <Alert variant="danger">{serverError}</Alert>}
      {saveOk && <Alert variant="success">Saved.</Alert>}

      <Form.Group className="mb-3">
        <Form.Label>Locations (comma-separated)</Form.Label>
        <Form.Control
          value={locationsDraft}
          onChange={(e) => setLocationsDraft(e.target.value)}
          placeholder="Bangalore, Remote"
        />
      </Form.Group>

      <div className="row">
        <div className="col-md-4 mb-3">
          <Form.Group>
            <Form.Label>Salary min</Form.Label>
            <Form.Control
              type="number"
              value={prefs.salaryMin ?? ""}
              onChange={(e) => setPrefs({ ...prefs, salaryMin: e.target.value ? Number(e.target.value) : undefined })}
            />
          </Form.Group>
        </div>
        <div className="col-md-4 mb-3">
          <Form.Group>
            <Form.Label>Salary max</Form.Label>
            <Form.Control
              type="number"
              value={prefs.salaryMax ?? ""}
              onChange={(e) => setPrefs({ ...prefs, salaryMax: e.target.value ? Number(e.target.value) : undefined })}
            />
          </Form.Group>
        </div>
        <div className="col-md-4 mb-3">
          <Form.Group>
            <Form.Label>Currency (3-letter)</Form.Label>
            <Form.Control
              value={prefs.currency ?? "INR"}
              maxLength={3}
              onChange={(e) => setPrefs({ ...prefs, currency: e.target.value.toUpperCase() })}
            />
          </Form.Group>
        </div>
      </div>

      <Form.Group className="mb-3">
        <Form.Switch
          id="prefs-remote"
          label="Open to remote roles"
          checked={!!prefs.remote}
          onChange={(e) => setPrefs({ ...prefs, remote: e.target.checked })}
        />
      </Form.Group>

      <Form.Group className="mb-3">
        <Form.Label>Employment types</Form.Label>
        <div>
          {EMPLOYMENT_TYPES.map((t) => (
            <Form.Check
              key={t}
              inline
              type="checkbox"
              id={`prefs-${t}`}
              label={t.replace("_", " ")}
              checked={(prefs.employmentTypes ?? []).includes(t)}
              onChange={() => toggleEmploymentType(t)}
            />
          ))}
        </div>
      </Form.Group>

      <Button onClick={save} disabled={saving || !isDirty}>
        {saving ? <Spinner size="sm" animation="border" /> : "Save preferences"}
      </Button>
    </div>
  );
}
