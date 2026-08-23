import React from 'react';
import { Link } from 'react-router-dom';

const Jobs: React.FC = () => {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', margin: 0 }}>Jobs</h2>
        <Link
          to="/jobs/create"
          style={{
            padding: '0.5rem 1rem',
            backgroundColor: '#2563eb',
            color: '#ffffff',
            borderRadius: '0.375rem',
            textDecoration: 'none',
            fontSize: '0.875rem',
            fontWeight: 500,
          }}
        >
          Create Job
        </Link>
      </div>
      <p style={{ color: '#4b5563' }}>Manage queued, running, retrying, completed, and cancelled workflow execution jobs.</p>
    </div>
  );
};

export default Jobs;
