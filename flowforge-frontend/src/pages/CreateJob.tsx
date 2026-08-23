import React from 'react';
import { Link } from 'react-router-dom';

const CreateJob: React.FC = () => {
  return (
    <div>
      <div style={{ marginBottom: '1rem' }}>
        <Link to="/jobs" style={{ color: '#2563eb', fontSize: '0.875rem', textDecoration: 'none' }}>
          &larr; Cancel
        </Link>
      </div>
      <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', margin: '0 0 1rem 0' }}>Create Job</h2>
      <p style={{ color: '#4b5563' }}>Queue a new job execution workload into FlowForge.</p>
    </div>
  );
};

export default CreateJob;
