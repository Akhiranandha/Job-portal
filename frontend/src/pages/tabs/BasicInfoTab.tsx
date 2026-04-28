import { useState } from "react";
import { useForm } from "react-hook-form";
import { Alert, Button, Form, Spinner } from "react-bootstrap";
import { updateBasicProfile } from "../../api/profile";
import { extractErrorMessage } from "../../api/client";
import type { BasicProfileUpdate, UserProfile } from "../../types/api";

interface Props {
  profile: UserProfile;
  onSaved: (next: UserProfile) => void;
}

export default function BasicInfoTab({ profile, onSaved }: Props) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<BasicProfileUpdate>({
    defaultValues: {
      firstName: profile.firstName,
      lastName: profile.lastName,
      phoneNumber: profile.phoneNumber ?? "",
      dateOfBirth: profile.dateOfBirth ?? "",
      address: profile.address ?? "",
      city: profile.city ?? "",
      state: profile.state ?? "",
      country: profile.country ?? "",
      postalCode: profile.postalCode ?? "",
      bio: profile.bio ?? "",
      linkedInUrl: profile.linkedInUrl ?? "",
      portfolioUrl: profile.portfolioUrl ?? "",
    },
  });

  const [serverError, setServerError] = useState<string | null>(null);
  const [saveOk, setSaveOk] = useState(false);

  const onSubmit = async (values: BasicProfileUpdate) => {
    setServerError(null);
    setSaveOk(false);
    // strip empties to avoid blanking already-set fields when ModelMapper null-skips
    const payload: BasicProfileUpdate = Object.fromEntries(
      Object.entries(values).filter(([, v]) => v !== "" && v != null)
    );
    try {
      const next = await updateBasicProfile(payload);
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

      <div className="row">
        <div className="col-md-6 mb-3">
          <Form.Group controlId="basic-firstName">
            <Form.Label>First name</Form.Label>
            <Form.Control
              isInvalid={!!errors.firstName}
              {...register("firstName", { required: "First name is required" })}
            />
            <Form.Control.Feedback type="invalid">{errors.firstName?.message}</Form.Control.Feedback>
          </Form.Group>
        </div>
        <div className="col-md-6 mb-3">
          <Form.Group controlId="basic-lastName">
            <Form.Label>Last name</Form.Label>
            <Form.Control
              isInvalid={!!errors.lastName}
              {...register("lastName", { required: "Last name is required" })}
            />
            <Form.Control.Feedback type="invalid">{errors.lastName?.message}</Form.Control.Feedback>
          </Form.Group>
        </div>
      </div>

      <Form.Group className="mb-3" controlId="basic-phone">
        <Form.Label>Phone</Form.Label>
        <Form.Control {...register("phoneNumber")} />
      </Form.Group>

      <Form.Group className="mb-3" controlId="basic-dob">
        <Form.Label>Date of birth</Form.Label>
        <Form.Control type="date" {...register("dateOfBirth")} />
      </Form.Group>

      <Form.Group className="mb-3" controlId="basic-address">
        <Form.Label>Address</Form.Label>
        <Form.Control {...register("address")} />
      </Form.Group>

      <div className="row">
        <div className="col-md-4 mb-3">
          <Form.Group controlId="basic-city"><Form.Label>City</Form.Label><Form.Control {...register("city")} /></Form.Group>
        </div>
        <div className="col-md-4 mb-3">
          <Form.Group controlId="basic-state"><Form.Label>State</Form.Label><Form.Control {...register("state")} /></Form.Group>
        </div>
        <div className="col-md-4 mb-3">
          <Form.Group controlId="basic-country"><Form.Label>Country</Form.Label><Form.Control {...register("country")} /></Form.Group>
        </div>
      </div>

      <Form.Group className="mb-3" controlId="basic-bio">
        <Form.Label>Bio</Form.Label>
        <Form.Control as="textarea" rows={3} {...register("bio")} />
      </Form.Group>

      <Form.Group className="mb-3" controlId="basic-linkedin">
        <Form.Label>LinkedIn URL</Form.Label>
        <Form.Control {...register("linkedInUrl")} />
      </Form.Group>

      <Form.Group className="mb-3" controlId="basic-portfolio">
        <Form.Label>Portfolio URL</Form.Label>
        <Form.Control {...register("portfolioUrl")} />
      </Form.Group>

      <Button type="submit" disabled={isSubmitting || !isDirty}>
        {isSubmitting ? <Spinner size="sm" animation="border" /> : "Save basic info"}
      </Button>
    </Form>
  );
}
