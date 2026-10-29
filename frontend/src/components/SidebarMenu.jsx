import { NavLink } from 'react-router-dom';

export default function SidebarMenu({ isOpen, onClose }) {
  return (
    <aside className={`sidebar ${isOpen ? 'sidebar-open' : 'sidebar-closed'}`}>
      <nav className="sidebar-nav">
        <NavLink to="/games" className="sidebar-link" onClick={onClose}>
          Games
        </NavLink>
        <NavLink
          to="/completed-games"
          className="sidebar-link"
          onClick={onClose}
        >
          Completed games
        </NavLink>
        <NavLink to="/profile" className="sidebar-link" onClick={onClose}>
          Profile
        </NavLink>
        <NavLink to="/friends" className="sidebar-link" onClick={onClose}>
          Friends
        </NavLink>
      </nav>
    </aside>
  );
}
