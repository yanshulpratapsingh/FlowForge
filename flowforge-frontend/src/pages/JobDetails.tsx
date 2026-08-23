import React from 'react';
import { useParams, Link } from 'react-router-dom';

const JobDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();

  return (
    <div>
      <div style={{ marginBottom: '1rem' }}>
        <Link to="/jobs" style={{ color: '#2563eb', fontSize: '0.875rem', textDecoration: 'none' }}>
          &larr; Back to Jobs
        </Link>
      </div>
      <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', margin: '0 0 1rem 0' }}>Job Details</h2>
      <p style={{ color: '#4b5563' }}>Detailed diagnostic execution log and status trace for Job ID: <strong>{id}</strong>.</p>
    </div>
  );
};

export default JobDetails;
