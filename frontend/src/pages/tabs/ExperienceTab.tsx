import { useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Form, Spinner } from "react-bootstrap";
import { updateExperience } from "../../api/profile";
import { extractErrorMessage } from "../../api/client";
import type { ExperienceEntry, UserProfile } from "../../types/api";

interface Props {
  profile: UserProfile;
  onSaved: (next: UserProfile) => void;
}

function emptyEntry(): ExperienceEntry {
  return { company: "", role: "", startDate: "", endDate: null, description: "" };
}

export default function ExperienceTab({ profile, onSaved }: Props) {
  const [entries, setEntries] = useState<ExperienceEntry[]>(profile.experience ?? []);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saveOk, setSaveOk] = useState(false);
  const initial = useRef<string>(JSON.stringify(profile.experience ?? []));

  useEffect(() => {
    initial.current = JSON.stringify(profile.experience ?? []);
    setEntries(profile.experience ?? []);
  }, [profile.experience]);

  const isDirty = JSON.stringify(entries) !== initial.current;

  function update(idx: number, patch: Partial<ExperienceEntry>) {
    setEntries((prev) => prev.map((e, i) => (i === idx ? { ...e, ...patch } : e)));
  }

  function addEntry() {
    setEntries((prev) => [...prev, emptyEntry()]);
  }

  function removeEntry(idx: number) {
    setEntries((prev) => prev.filter((_, i) => i !== idx));
  }

  async function save() {
    setServerError(null);
    setSaveOk(false);
    setSaving(true);
    try {
      // Backend expects null for "current job" endDate.
      const cleaned = entries.map((e) => ({
        ...e,
        endDate: e.endDate === "" ? null : e.endDate,
      }));
      const next = await updateExperience(cleaned);
      onSaved(next);
      initial.current = JSON.stringify(next.experience ?? []);
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

      {entries.length === 0 && <p className="text-muted">No experience entries yet.</p>}

      {entries.map((entry, idx) => (
        <Card key={idx} className="mb-3">
          <Card.Body>
            <div className="d-flex justify-content-between mb-2">
              <strong>Entry {idx + 1}</strong>
              <Button size="sm" variant="outline-danger" onClick={() => removeEntry(idx)}>Delete</Button>
            </div>
            <div className="row">
              <div className="col-md-6 mb-2">
                <Form.Group>
                  <Form.Label>Company</Form.Label>
                  <Form.Control value={entry.company} onChange={(e) => update(idx, { company: e.target.value })} />
                </Form.Group>
              </div>
              <div className="col-md-6 mb-2">
                <Form.Group>
                  <Form.Label>Role</Form.Label>
                  <Form.Control value={entry.role} onChange={(e) => update(idx, { role: e.target.value })} />
                </Form.Group>
              </div>
              <div className="col-md-6 mb-2">
                <Form.Group>
                  <Form.Label>Start (YYYY-MM)</Form.Label>
                  <Form.Control
                    placeholder="2022-01"
                    value={entry.startDate}
                    onChange={(e) => update(idx, { startDate: e.target.value })}
                  />
                </Form.Group>
              </div>
              <div className="col-md-6 mb-2">
                <Form.Group>
                  <Form.Label>End (YYYY-MM, blank = current)</Form.Label>
                  <Form.Control
                    placeholder="2024-06"
                    value={entry.endDate ?? ""}
                    onChange={(e) => update(idx, { endDate: e.target.value || null })}
                  />
                </Form.Group>
              </div>
            </div>
            <Form.Group>
              <Form.Label>Description</Form.Label>
              <Form.Control
                as="textarea"
                rows={2}
                value={entry.description ?? ""}
                onChange={(e) => update(idx, { description: e.target.value })}
              />
            </Form.Group>
          </Card.Body>
        </Card>
      ))}

      <Button variant="outline-primary" onClick={addEntry} className="me-2">+ Add entry</Button>
      <Button onClick={save} disabled={saving || !isDirty}>
        {saving ? <Spinner size="sm" animation="border" /> : "Save experience"}
      </Button>
    </div>
  );
}
