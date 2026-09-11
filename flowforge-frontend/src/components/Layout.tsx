import { useState, useEffect } from 'react';
import type { FC } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { LayoutDashboard, FileText, BarChart3, Users, Menu, X, Layers } from 'lucide-react';

export const Layout: FC = () => {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState<boolean>(false);

  const navItems = [
    { to: '/', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/jobs', label: 'Jobs', icon: FileText },
    { to: '/analytics', label: 'Analytics', icon: BarChart3 },
    { to: '/workers', label: 'Workers', icon: Users },
  ];

  // Close on Escape key press
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isMobileMenuOpen) {
        setIsMobileMenuOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isMobileMenuOpen]);

  const navLinks = (
    <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
      {navItems.map((item) => (
        <li key={item.to} style={{ marginBottom: '0.5rem' }}>
          <NavLink
            to={item.to}
            onClick={() => setIsMobileMenuOpen(false)}
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
              transition: 'all 0.15s ease',
            })}
          >
            <item.icon size={18} />
            {item.label}
          </NavLink>
        </li>
      ))}
    </ul>
  );

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#f3f4f6' }}>
      {/* Desktop Sidebar */}
      <aside className="desktop-sidebar" aria-label="Desktop Navigation">
        <div
          style={{
            padding: '1.25rem 1.5rem',
            fontSize: '1.25rem',
            fontWeight: 700,
            borderBottom: '1px solid #374151',
            letterSpacing: '0.05em',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <Layers size={22} color="#3b82f6" />
          <span><span style={{ color: '#3b82f6' }}>Flow</span>Forge</span>
        </div>
        <nav style={{ flex: 1, padding: '1rem' }}>
          {navLinks}
        </nav>
      </aside>

      {/* Mobile Drawer Backdrop */}
      {isMobileMenuOpen && (
        <div
          onClick={() => setIsMobileMenuOpen(false)}
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            zIndex: 40,
            backdropFilter: 'blur(2px)',
          }}
          aria-hidden="true"
        />
      )}

      {/* Mobile Drawer Content */}
      <div
        style={{
          position: 'fixed',
          top: 0,
          bottom: 0,
          left: isMobileMenuOpen ? 0 : '-280px',
          width: '260px',
          backgroundColor: '#1f2937',
          color: '#ffffff',
          zIndex: 50,
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '4px 0 12px rgba(0, 0, 0, 0.2)',
          transition: 'left 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
        aria-label="Mobile Navigation Drawer"
      >
        <div
          style={{
            padding: '1.15rem 1.25rem',
            borderBottom: '1px solid #374151',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '1.15rem', fontWeight: 700 }}>
            <Layers size={20} color="#3b82f6" />
            <span><span style={{ color: '#3b82f6' }}>Flow</span>Forge</span>
          </div>
          <button
            onClick={() => setIsMobileMenuOpen(false)}
            aria-label="Close Navigation Menu"
            style={{
              background: 'none',
              border: 'none',
              color: '#9ca3af',
              cursor: 'pointer',
              padding: '0.25rem',
            }}
          >
            <X size={20} />
          </button>
        </div>
        <nav style={{ flex: 1, padding: '1rem' }}>
          {navLinks}
        </nav>
      </div>

      {/* Main View Area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {/* Desktop Header */}
        <header className="desktop-header">
          <div style={{ fontWeight: 600, color: '#374151', fontSize: '0.925rem' }}>
            System Operations Control Panel
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span
              style={{
                width: '8px',
                height: '8px',
                backgroundColor: '#10b981',
                borderRadius: '50%',
                display: 'inline-block',
              }}
            />
            <span style={{ fontSize: '0.85rem', color: '#4b5563', fontWeight: 500 }}>
              Live Cluster Connected
            </span>
          </div>
        </header>

        {/* Mobile Sticky Top Header */}
        <div className="mobile-nav-bar">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700, fontSize: '1.1rem' }}>
            <Layers size={20} color="#3b82f6" />
            <span><span style={{ color: '#3b82f6' }}>Flow</span>Forge</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span
              style={{
                width: '7px',
                height: '7px',
                backgroundColor: '#10b981',
                borderRadius: '50%',
                display: 'inline-block',
              }}
            />
            <button
              onClick={() => setIsMobileMenuOpen(true)}
              aria-label="Open Navigation Menu"
              style={{
                background: 'none',
                border: 'none',
                color: '#ffffff',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                padding: '0.25rem',
              }}
            >
              <Menu size={22} />
            </button>
          </div>
        </div>

        {/* Dynamic Route Viewport */}
        <main className="app-main-viewport">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
