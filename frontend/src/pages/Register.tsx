import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Container,
  Form,
  Spinner,
  ToggleButton,
  ToggleButtonGroup,
} from "react-bootstrap";
import { api, extractErrorMessage, extractFieldErrors } from "../api/client";
import type { ApiResponse, RegisterRequest, Role, UserProfile } from "../types/api";

interface RegisterForm {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
}

export default function Register() {
  const navigate = useNavigate();

  const [role, setRole] = useState<Role>("JOB_SEEKER");
  const [serverError, setServerError] = useState<string | null>(null);
  const [serverFieldErrors, setServerFieldErrors] = useState<Record<string, string>>({});

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>();

  const onSubmit = async (values: RegisterForm) => {
    setServerError(null);
    setServerFieldErrors({});

    const payload: RegisterRequest = { ...values, role };

    try {
      await api.post<ApiResponse<UserProfile>>("/api/users/public/register", payload);
      navigate("/login", {
        state: { justRegistered: true, email: values.email },
        replace: true,
      });
    } catch (err) {
      setServerError(extractErrorMessage(err, "Registration failed"));
      const fieldErrs = extractFieldErrors(err);
      if (fieldErrs) setServerFieldErrors(fieldErrs);
    }
  };

  function fieldErrorMessage(name: keyof RegisterForm) {
    return errors[name]?.message ?? serverFieldErrors[name];
  }

  return (
    <Container style={{ maxWidth: 600 }}>
      <Card>
        <Card.Body>
          <Card.Title as="h1" className="h4 mb-3">
            Create your account
          </Card.Title>

          {serverError && <Alert variant="danger">{serverError}</Alert>}

          <Form onSubmit={handleSubmit(onSubmit)} noValidate>
            <Form.Group className="mb-3">
              <Form.Label>I am a…</Form.Label>
              <div>
                <ToggleButtonGroup
                  type="radio"
                  name="role-toggle"
                  value={role}
                  onChange={(v) => setRole(v as Role)}
                >
                  <ToggleButton id="role-jobseeker" value="JOB_SEEKER" variant="outline-primary">
                    Candidate
                  </ToggleButton>
                  <ToggleButton id="role-recruiter" value="RECRUITER" variant="outline-primary">
                    Recruiter
                  </ToggleButton>
                </ToggleButtonGroup>
              </div>
            </Form.Group>

            <Form.Group className="mb-3" controlId="reg-email">
              <Form.Label>Email</Form.Label>
              <Form.Control
                type="email"
                isInvalid={!!fieldErrorMessage("email")}
                {...register("email", { required: "Email is required" })}
              />
              <Form.Control.Feedback type="invalid">
                {fieldErrorMessage("email")}
              </Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3" controlId="reg-password">
              <Form.Label>Password</Form.Label>
              <Form.Control
                type="password"
                isInvalid={!!fieldErrorMessage("password")}
                {...register("password", {
                  required: "Password is required",
                  minLength: { value: 8, message: "At least 8 characters" },
                })}
              />
              <Form.Control.Feedback type="invalid">
                {fieldErrorMessage("password")}
              </Form.Control.Feedback>
            </Form.Group>

            <div className="row">
              <div className="col-md-6 mb-3">
                <Form.Group controlId="reg-firstName">
                  <Form.Label>First name</Form.Label>
                  <Form.Control
                    isInvalid={!!fieldErrorMessage("firstName")}
                    {...register("firstName", { required: "First name is required" })}
                  />
                  <Form.Control.Feedback type="invalid">
                    {fieldErrorMessage("firstName")}
                  </Form.Control.Feedback>
                </Form.Group>
              </div>
              <div className="col-md-6 mb-3">
                <Form.Group controlId="reg-lastName">
                  <Form.Label>Last name</Form.Label>
                  <Form.Control
                    isInvalid={!!fieldErrorMessage("lastName")}
                    {...register("lastName", { required: "Last name is required" })}
                  />
                  <Form.Control.Feedback type="invalid">
                    {fieldErrorMessage("lastName")}
                  </Form.Control.Feedback>
                </Form.Group>
              </div>
            </div>

            <Form.Group className="mb-3" controlId="reg-phone">
              <Form.Label>Phone (optional)</Form.Label>
              <Form.Control {...register("phoneNumber")} />
            </Form.Group>

            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? <Spinner size="sm" animation="border" /> : "Register"}
            </Button>
          </Form>

          <div className="mt-3">
            <small>
              Already have an account? <Link to="/login">Sign in</Link>
            </small>
          </div>
        </Card.Body>
      </Card>
    </Container>
  );
}
