// The routes. Pages arrive step by step (plan Phase 5): login and the analyses list now; new analysis, live analysis,
// approvals, history and admin follow. Anything unknown goes home (or to login when there is no session).
import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './Layout'
import AdminOnly from './pages/AdminOnly'
import AdminPage from './pages/AdminPage'
import AdminSitePage from './pages/AdminSitePage'
import AnalysesPage from './pages/AnalysesPage'
import AnalysisPage from './pages/AnalysisPage'
import ApprovalsPage from './pages/ApprovalsPage'
import Login from './pages/Login'
import NewAnalysisPage from './pages/NewAnalysisPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Layout />}>
        <Route index element={<AnalysesPage />} />
        <Route path="new" element={<NewAnalysisPage />} />
        <Route path="analyses/:id" element={<AnalysisPage />} />
        <Route path="approvals" element={<ApprovalsPage />} />
        <Route element={<AdminOnly />}>
          <Route path="admin" element={<AdminPage />} />
          <Route path="admin/sites/:id" element={<AdminSitePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
