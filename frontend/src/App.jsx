import { Route, Routes } from 'react-router-dom';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './AuthContext.jsx';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegistrationPage';
import HomePage from './pages/HomePage';
import NotFoundPage from './pages/NotFoundPage';
import GamesPage from './pages/GamesPage';
import GamePage from './pages/GamePage';
import ProfilePage from './pages/ProfilePage';
import AppLayout from './components/AppLayout';
import ProtectedRoute from './components/ProtectedRoute';
import './App.css';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<AppLayout />}>
            {/* Pages with topbar+sidemenu, log in not required */}
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route element={<ProtectedRoute />}>
              {/* Pages with topbar+sidemenu, log in required*/}
              <Route path="/games" element={<GamesPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route
                path="/games/:gameId"
                element={<GamePage />}
              />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
