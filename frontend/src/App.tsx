import { Navigate, Route, Routes } from "react-router-dom";
import NavBar from "./components/NavBar";
import { RequireAuth } from "./auth/RequireAuth";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Profile from "./pages/Profile";
import ProfileEdit from "./pages/ProfileEdit";
import Security from "./pages/Security";
import Placeholder from "./pages/Placeholder";

export default function App() {
  return (
    <div className="app-shell">
      <NavBar />
      <main className="app-content">
        <Routes>
          <Route path="/" element={<Navigate to="/profile" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          <Route element={<RequireAuth />}>
            <Route path="/profile" element={<Profile />} />
            <Route path="/profile/edit" element={<ProfileEdit />} />
            <Route path="/settings/security" element={<Security />} />
          </Route>

          <Route element={<RequireAuth allowedRoles={["JOB_SEEKER", "ADMIN"]} />}>
            <Route
              path="/matches"
              element={<Placeholder title="Matches" phase="Phase 2 (Matching Service, FR-7)" />}
            />
          </Route>

          <Route element={<RequireAuth allowedRoles={["RECRUITER", "ADMIN"]} />}>
            <Route
              path="/my-jobs"
              element={<Placeholder title="My jobs" phase="Phase 1 (Job Service, FR-5)" />}
            />
          </Route>

          <Route path="*" element={<Navigate to="/profile" replace />} />
        </Routes>
      </main>
    </div>
  );
}
