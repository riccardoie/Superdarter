import { NavLink } from 'react-router-dom';

export default function SidebarMenu({ isOpen, onClose }) {
  return (
    <aside className={`sidebar ${isOpen ? 'sidebar-open' : 'sidebar-closed'}`}>
      <nav className="sidebar-nav">
        <NavLink to="/games" className="sidebar-link" onClick={onClose}>
          Games
        </NavLink>
        <NavLink to="/profile" className="sidebar-link" onClick={onClose}>
          Profile
        </NavLink>
      </nav>
    </aside>
  );
}
