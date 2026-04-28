import { useState } from "react";
import { useForm } from "react-hook-form";
import { Alert, Button, Form, Spinner } from "react-bootstrap";
import { updateRecruiterProfile } from "../../api/profile";
import { extractErrorMessage } from "../../api/client";
import type { RecruiterProfileRequest, UserProfile } from "../../types/api";

interface Props {
  profile: UserProfile;
  onSaved: (next: UserProfile) => void;
}

export default function CompanyTab({ profile, onSaved }: Props) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<RecruiterProfileRequest>({
    defaultValues: {
      companyName: profile.companyName ?? "",
      designation: profile.designation ?? "",
      companyWebsite: profile.companyWebsite ?? "",
    },
  });

  const [serverError, setServerError] = useState<string | null>(null);
  const [saveOk, setSaveOk] = useState(false);

  const onSubmit = async (values: RecruiterProfileRequest) => {
    setServerError(null);
    setSaveOk(false);
    try {
      const next = await updateRecruiterProfile(values);
      onSaved(next);
      setSaveOk(true);
    } catch (err) {
      setServerError(extractErrorMessage(err, "Save failed"));
    }
  };

  return (
    <Form onSubmit={handleSubmit(onSubmit)} noValidate>
      {serverError && <Alert variant="danger">{serverError}</Alert>}
      {saveOk && <Alert variant="success">Saved.</Alert>}

      <Form.Group className="mb-3" controlId="recruiter-companyName">
        <Form.Label>Company name</Form.Label>
        <Form.Control
          isInvalid={!!errors.companyName}
          {...register("companyName", { required: "Company name is required" })}
        />
        <Form.Control.Feedback type="invalid">{errors.companyName?.message}</Form.Control.Feedback>
      </Form.Group>

      <Form.Group className="mb-3" controlId="recruiter-designation">
        <Form.Label>Designation</Form.Label>
        <Form.Control {...register("designation")} />
      </Form.Group>

      <Form.Group className="mb-3" controlId="recruiter-website">
        <Form.Label>Company website</Form.Label>
        <Form.Control
          placeholder="https://acme.example"
          isInvalid={!!errors.companyWebsite}
          {...register("companyWebsite", {
            pattern: {
              value: /^$|^https?:\/\/[^\s/$.?#].[^\s]*$/,
              message: "Must be a valid http(s) URL",
            },
          })}
        />
        <Form.Control.Feedback type="invalid">{errors.companyWebsite?.message}</Form.Control.Feedback>
      </Form.Group>

      <Button type="submit" disabled={isSubmitting || !isDirty}>
        {isSubmitting ? <Spinner size="sm" animation="border" /> : "Save company info"}
      </Button>
    </Form>
  );
}
