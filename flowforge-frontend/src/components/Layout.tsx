import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { LayoutDashboard, FileText, BarChart3, Users } from 'lucide-react';

export const Layout: React.FC = () => {
  const navItems = [
    { to: '/', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/jobs', label: 'Jobs', icon: FileText },
    { to: '/analytics', label: 'Analytics', icon: BarChart3 },
    { to: '/workers', label: 'Workers', icon: Users },
  ];

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#f3f4f6', fontFamily: 'sans-serif' }}>
      {/* Sidebar */}
      <aside
        style={{
          width: '260px',
          backgroundColor: '#1f2937',
          color: '#ffffff',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '2px 0 5px rgba(0,0,0,0.1)',
        }}
      >
        <div
          style={{
            padding: '1.5rem',
            fontSize: '1.25rem',
            fontWeight: 'bold',
            borderBottom: '1px solid #374151',
            letterSpacing: '0.05em',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <span style={{ color: '#3b82f6' }}>Flow</span>Forge
        </div>
        <nav style={{ flex: 1, padding: '1rem' }}>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {navItems.map((item) => (
              <li key={item.to} style={{ marginBottom: '0.5rem' }}>
                <NavLink
                  to={item.to}
                  style={({ isActive }) => ({
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.75rem',
                    padding: '0.75rem 1rem',
                    color: isActive ? '#ffffff' : '#9ca3af',
                    backgroundColor: isActive ? '#374151' : 'transparent',
                    borderRadius: '0.375rem',
                    textDecoration: 'none',
                    fontWeight: 500,
                    fontSize: '0.9rem',
                    transition: 'all 0.2s',
                  })}
                >
                  <item.icon size={18} />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </aside>

      {/* Main Content */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <header
          style={{
            height: '64px',
            backgroundColor: '#ffffff',
            borderBottom: '1px solid #e5e7eb',
            display: 'flex',
            alignItems: 'center',
            padding: '0 2rem',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ fontWeight: 500, color: '#374151' }}>System Operations Control Panel</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span
              style={{
                width: '8px',
                height: '8px',
                backgroundColor: '#10b981',
                borderRadius: '50%',
              }}
            />
            <span style={{ fontSize: '0.875rem', color: '#6b7280', fontWeight: 500 }}>Live Cluster Connected</span>
          </div>
        </header>
        <main style={{ flex: 1, padding: '2rem', overflowY: 'auto' }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};
