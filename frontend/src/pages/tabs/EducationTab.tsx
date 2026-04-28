import { useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Form, Spinner } from "react-bootstrap";
import { updateEducation } from "../../api/profile";
import { extractErrorMessage } from "../../api/client";
import type { EducationEntry, UserProfile } from "../../types/api";

interface Props {
  profile: UserProfile;
  onSaved: (next: UserProfile) => void;
}

function empty(): EducationEntry {
  return { institution: "", degree: "", field: "", startYear: new Date().getFullYear(), endYear: null };
}

export default function EducationTab({ profile, onSaved }: Props) {
  const [entries, setEntries] = useState<EducationEntry[]>(profile.education ?? []);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saveOk, setSaveOk] = useState(false);
  const initial = useRef<string>(JSON.stringify(profile.education ?? []));

  useEffect(() => {
    initial.current = JSON.stringify(profile.education ?? []);
    setEntries(profile.education ?? []);
  }, [profile.education]);

  const isDirty = JSON.stringify(entries) !== initial.current;

  function update(idx: number, patch: Partial<EducationEntry>) {
    setEntries((prev) => prev.map((e, i) => (i === idx ? { ...e, ...patch } : e)));
  }

  async function save() {
    setServerError(null);
    setSaveOk(false);
    setSaving(true);
    try {
      const cleaned = entries.map((e) => ({
        ...e,
        endYear: e.endYear == null || (e.endYear as unknown as string) === "" ? null : Number(e.endYear),
        startYear: Number(e.startYear),
      }));
      const next = await updateEducation(cleaned);
      onSaved(next);
      initial.current = JSON.stringify(next.education ?? []);
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

      {entries.length === 0 && <p className="text-muted">No education entries yet.</p>}

      {entries.map((entry, idx) => (
        <Card key={idx} className="mb-3">
          <Card.Body>
            <div className="d-flex justify-content-between mb-2">
              <strong>Entry {idx + 1}</strong>
              <Button size="sm" variant="outline-danger" onClick={() => setEntries((p) => p.filter((_, i) => i !== idx))}>
                Delete
              </Button>
            </div>
            <div className="row">
              <div className="col-md-6 mb-2">
                <Form.Group>
                  <Form.Label>Institution</Form.Label>
                  <Form.Control value={entry.institution} onChange={(e) => update(idx, { institution: e.target.value })} />
                </Form.Group>
              </div>
              <div className="col-md-6 mb-2">
                <Form.Group>
                  <Form.Label>Degree</Form.Label>
                  <Form.Control value={entry.degree} onChange={(e) => update(idx, { degree: e.target.value })} />
                </Form.Group>
              </div>
              <div className="col-md-6 mb-2">
                <Form.Group>
                  <Form.Label>Field</Form.Label>
                  <Form.Control value={entry.field} onChange={(e) => update(idx, { field: e.target.value })} />
                </Form.Group>
              </div>
              <div className="col-md-3 mb-2">
                <Form.Group>
                  <Form.Label>Start year</Form.Label>
                  <Form.Control
                    type="number"
                    value={entry.startYear}
                    onChange={(e) => update(idx, { startYear: Number(e.target.value) })}
                  />
                </Form.Group>
              </div>
              <div className="col-md-3 mb-2">
                <Form.Group>
                  <Form.Label>End year (blank = current)</Form.Label>
                  <Form.Control
                    type="number"
                    value={entry.endYear ?? ""}
                    onChange={(e) => update(idx, { endYear: e.target.value ? Number(e.target.value) : null })}
                  />
                </Form.Group>
              </div>
            </div>
          </Card.Body>
        </Card>
      ))}

      <Button variant="outline-primary" onClick={() => setEntries((p) => [...p, empty()])} className="me-2">
        + Add entry
      </Button>
      <Button onClick={save} disabled={saving || !isDirty}>
        {saving ? <Spinner size="sm" animation="border" /> : "Save education"}
      </Button>
    </div>
  );
}
