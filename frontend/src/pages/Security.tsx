import { useState } from "react";
import { useForm } from "react-hook-form";
import { Alert, Button, Card, Container, Form, Modal, Spinner } from "react-bootstrap";
import { api, extractErrorMessage } from "../api/client";
import { deleteAccount } from "../api/profile";
import { useAuth } from "../auth/AuthContext";
import type { PasswordUpdateRequest } from "../types/api";

export default function Security() {
  const { logout } = useAuth();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<PasswordUpdateRequest>();

  const [pwError, setPwError] = useState<string | null>(null);
  const [pwOk, setPwOk] = useState(false);

  const onChangePassword = async (values: PasswordUpdateRequest) => {
    setPwError(null);
    setPwOk(false);
    try {
      await api.put("/auth/password", values);
      setPwOk(true);
      reset();
    } catch (err) {
      setPwError(extractErrorMessage(err, "Password change failed"));
    }
  };

  const [showDelete, setShowDelete] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  async function confirmDelete() {
    setDeleteError(null);
    setDeleting(true);
    try {
      await deleteAccount();
      logout();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Account deletion failed"));
      setDeleting(false);
    }
  }

  return (
    <Container style={{ maxWidth: 600 }}>
      <h1 className="h4 mb-3">Security</h1>

      <Card className="mb-4">
        <Card.Body>
          <h2 className="h5 mb-3">Change password</h2>

          {pwError && <Alert variant="danger">{pwError}</Alert>}
          {pwOk && <Alert variant="success">Password updated.</Alert>}

          <Form onSubmit={handleSubmit(onChangePassword)} noValidate>
            <Form.Group className="mb-3" controlId="pw-current">
              <Form.Label>Current password</Form.Label>
              <Form.Control
                type="password"
                isInvalid={!!errors.currentPassword}
                {...register("currentPassword", { required: "Current password is required" })}
              />
              <Form.Control.Feedback type="invalid">{errors.currentPassword?.message}</Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3" controlId="pw-new">
              <Form.Label>New password</Form.Label>
              <Form.Control
                type="password"
                isInvalid={!!errors.newPassword}
                {...register("newPassword", {
                  required: "New password is required",
                  minLength: { value: 8, message: "At least 8 characters" },
                })}
              />
              <Form.Control.Feedback type="invalid">{errors.newPassword?.message}</Form.Control.Feedback>
            </Form.Group>

            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? <Spinner size="sm" animation="border" /> : "Update password"}
            </Button>
          </Form>
        </Card.Body>
      </Card>

      <Card border="danger">
        <Card.Body>
          <h2 className="h5 mb-3 text-danger">Delete account</h2>
          <p className="small">
            Soft-deletes your account. You won't be able to log in afterwards.
          </p>
          <Button variant="outline-danger" onClick={() => setShowDelete(true)}>
            Delete my account
          </Button>
        </Card.Body>
      </Card>

      <Modal show={showDelete} onHide={() => !deleting && setShowDelete(false)}>
        <Modal.Header closeButton>
          <Modal.Title>Delete account?</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {deleteError && <Alert variant="danger">{deleteError}</Alert>}
          This action soft-deletes your account. Are you sure?
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowDelete(false)} disabled={deleting}>
            Cancel
          </Button>
          <Button variant="danger" onClick={confirmDelete} disabled={deleting}>
            {deleting ? <Spinner size="sm" animation="border" /> : "Delete"}
          </Button>
        </Modal.Footer>
      </Modal>
    </Container>
  );
}
