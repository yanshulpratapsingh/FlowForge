import React from 'react';

const Dashboard: React.FC = () => {
  return (
    <div>
      <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', margin: '0 0 1rem 0' }}>Dashboard</h2>
      <p style={{ color: '#4b5563' }}>Welcome to FlowForge. Choose a section from the sidebar to manage jobs, view analytics, or check worker pools.</p>
    </div>
  );
};

export default Dashboard;
