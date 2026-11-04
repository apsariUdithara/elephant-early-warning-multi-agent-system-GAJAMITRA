// The routes. Pages arrive step by step (plan Phase 5): login and the analyses list now; new analysis, live analysis,
// approvals, history and admin follow. Anything unknown goes home (or to login when there is no session).
import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './Layout'
import AnalysesPage from './pages/AnalysesPage'
import Login from './pages/Login'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Layout />}>
        <Route index element={<AnalysesPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
