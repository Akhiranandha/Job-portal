import { Alert, Container } from "react-bootstrap";

interface Props {
  title: string;
  phase: string;
}

export default function Placeholder({ title, phase }: Props) {
  return (
    <Container style={{ maxWidth: 700 }}>
      <h1 className="h4">{title}</h1>
      <Alert variant="info">
        Coming in {phase}. The backend service for this page hasn't shipped yet — see
        <code className="ms-1">docs/ROADMAP.md</code>.
      </Alert>
    </Container>
  );
}
