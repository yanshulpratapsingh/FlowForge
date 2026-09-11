import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import Dashboard from './pages/Dashboard';
import Jobs from './pages/Jobs';
import JobDetails from './pages/JobDetails';
import CreateJob from './pages/CreateJob';
import Analytics from './pages/Analytics';
import Workers from './pages/Workers';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="dashboard" element={<Navigate to="/" replace />} />
          <Route path="jobs" element={<Jobs />} />
          <Route path="jobs/create" element={<CreateJob />} />
          <Route path="create-job" element={<Navigate to="/jobs/create" replace />} />
          <Route path="jobs/:id" element={<JobDetails />} />
          <Route path="analytics" element={<Analytics />} />
          <Route path="workers" element={<Workers />} />
          <Route path="*" element={<div>Page Not Found</div>} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
