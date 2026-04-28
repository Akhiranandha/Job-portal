import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Alert, Button, Card, Container, Form, Spinner } from "react-bootstrap";
import { useAuth } from "../auth/AuthContext";
import { extractErrorMessage } from "../api/client";
import type { Role } from "../types/api";

interface LoginForm {
  email: string;
  password: string;
}

const LANDING_BY_ROLE: Record<Role, string> = {
  JOB_SEEKER: "/matches",
  RECRUITER: "/my-jobs",
  ADMIN: "/profile",
};

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const fromPath = (location.state as { from?: string } | null)?.from;

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>();

  const [serverError, setServerError] = useState<string | null>(null);

  const onSubmit = async (values: LoginForm) => {
    setServerError(null);
    try {
      const resp = await login(values.email, values.password);
      navigate(fromPath ?? LANDING_BY_ROLE[resp.role], { replace: true });
    } catch (err) {
      setServerError(extractErrorMessage(err, "Login failed"));
    }
  };

  return (
    <Container style={{ maxWidth: 480 }}>
      <Card>
        <Card.Body>
          <Card.Title as="h1" className="h4 mb-3">
            Sign in
          </Card.Title>

          {serverError && <Alert variant="danger">{serverError}</Alert>}

          <Form onSubmit={handleSubmit(onSubmit)} noValidate>
            <Form.Group className="mb-3" controlId="login-email">
              <Form.Label>Email</Form.Label>
              <Form.Control
                type="email"
                isInvalid={!!errors.email}
                {...register("email", { required: "Email is required" })}
              />
              <Form.Control.Feedback type="invalid">
                {errors.email?.message}
              </Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3" controlId="login-password">
              <Form.Label>Password</Form.Label>
              <Form.Control
                type="password"
                isInvalid={!!errors.password}
                {...register("password", { required: "Password is required" })}
              />
              <Form.Control.Feedback type="invalid">
                {errors.password?.message}
              </Form.Control.Feedback>
            </Form.Group>

            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? <Spinner size="sm" animation="border" /> : "Sign in"}
            </Button>
          </Form>

          <div className="mt-3">
            <small>
              No account? <Link to="/register">Create one</Link>
            </small>
          </div>
        </Card.Body>
      </Card>
    </Container>
  );
}
