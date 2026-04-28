import { useState, useRef, useEffect, type KeyboardEvent } from "react";
import { Alert, Badge, Button, Form, Spinner } from "react-bootstrap";
import { updateSkills } from "../../api/profile";
import { extractErrorMessage } from "../../api/client";
import type { UserProfile } from "../../types/api";

interface Props {
  profile: UserProfile;
  onSaved: (next: UserProfile) => void;
}

export default function SkillsTab({ profile, onSaved }: Props) {
  const [skills, setSkills] = useState<string[]>(profile.skills ?? []);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saveOk, setSaveOk] = useState(false);
  const initialSkills = useRef<string[]>(profile.skills ?? []);

  useEffect(() => {
    initialSkills.current = profile.skills ?? [];
    setSkills(profile.skills ?? []);
  }, [profile.skills]);

  const isDirty =
    skills.length !== initialSkills.current.length ||
    skills.some((s, i) => s !== initialSkills.current[i]);

  function add() {
    const next = draft.trim();
    if (!next) return;
    if (skills.includes(next)) {
      setDraft("");
      return;
    }
    setSkills((prev) => [...prev, next]);
    setDraft("");
  }

  function remove(s: string) {
    setSkills((prev) => prev.filter((x) => x !== s));
  }

  function onKey(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      add();
    }
  }

  async function save() {
    setServerError(null);
    setSaveOk(false);
    setSaving(true);
    try {
      const next = await updateSkills(skills);
      onSaved(next);
      initialSkills.current = next.skills ?? [];
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
        <Form.Label>Add a skill</Form.Label>
        <div className="d-flex">
          <Form.Control
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKey}
            placeholder="Type a skill and press Enter"
          />
          <Button variant="outline-primary" className="ms-2" onClick={add}>Add</Button>
        </div>
        <Form.Text>Type and press Enter to add. Click × to remove.</Form.Text>
      </Form.Group>

      <div className="mb-3">
        {skills.length === 0 && <span className="text-muted">No skills yet.</span>}
        {skills.map((s) => (
          <Badge key={s} bg="info" className="me-1 mb-1" style={{ cursor: "pointer" }}>
            {s}
            <span
              className="ms-1"
              role="button"
              aria-label={`Remove ${s}`}
              onClick={() => remove(s)}
            >
              ×
            </span>
          </Badge>
        ))}
      </div>

      <Button onClick={save} disabled={saving || !isDirty}>
        {saving ? <Spinner size="sm" animation="border" /> : "Save skills"}
      </Button>
    </div>
  );
}
