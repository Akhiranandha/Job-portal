import { Container, Nav, Navbar } from "react-bootstrap";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function NavBar() {
  const { auth, logout } = useAuth();
  const navigate = useNavigate();

  function go(path: string) {
    return (e: React.MouseEvent) => {
      e.preventDefault();
      navigate(path);
    };
  }

  return (
    <Navbar bg="dark" variant="dark" expand="md">
      <Container>
        <Navbar.Brand as={Link} to={auth ? "/profile" : "/login"}>
          Job Portal
        </Navbar.Brand>

        <Navbar.Toggle aria-controls="main-nav" />
        <Navbar.Collapse id="main-nav">
          <Nav className="me-auto">
            {auth?.role === "JOB_SEEKER" && (
              <Nav.Link href="/matches" onClick={go("/matches")}>Matches</Nav.Link>
            )}
            {auth?.role === "RECRUITER" && (
              <Nav.Link href="/my-jobs" onClick={go("/my-jobs")}>My jobs</Nav.Link>
            )}
            {auth && (
              <Nav.Link href="/profile" onClick={go("/profile")}>Profile</Nav.Link>
            )}
            {auth && (
              <Nav.Link href="/settings/security" onClick={go("/settings/security")}>
                Security
              </Nav.Link>
            )}
          </Nav>

          <Nav>
            {auth ? (
              <>
                <Navbar.Text className="me-3">
                  {auth.email} · <em>{auth.role}</em>
                </Navbar.Text>
                <Nav.Link onClick={logout}>Log out</Nav.Link>
              </>
            ) : (
              <>
                <Nav.Link href="/login" onClick={go("/login")}>Sign in</Nav.Link>
                <Nav.Link href="/register" onClick={go("/register")}>Register</Nav.Link>
              </>
            )}
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}
