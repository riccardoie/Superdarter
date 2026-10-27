import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../useAuth';
import { register } from '../api';

export default function RegisterPage() {
  const [formData, setFormData] = useState({});
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleChange = (event) => {
    setFormData({ ...formData, [event.target.name]: event.target.value });
  };

  async function handleSubmit(e) {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const res = await register(formData);
      login(res.token);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page auth-page">
      <div className="card">
        <h1>Register</h1>
        <form className="form" onSubmit={handleSubmit}>
          <div>
            <label>Username: </label>
            <input
              label="Username"
              name="username"
              required
              pattern={'\\S+'}
              title="Username must not contain spaces or whitespace"
              aria-describedby="username-requirements"
              value={formData['username'] || ''}
              onChange={handleChange}
            />
            <small id="username-requirements">No spaces or whitespace allowed.</small>
          </div>
          <div>
            <label>E-mail: </label>
            <input
              label="email"
              name="email"
              type="email"
              required
              value={formData['email'] || ''}
              onChange={handleChange}
            />
          </div>
          <div>
            <label>Password: </label>
            <input
              label="Password"
              name="password"
              type="password"
              required
              minLength={8}
              pattern={'[\\s\\S]*[0-9][\\s\\S]*'}
              title="Password must be at least 8 characters long and contain at least one number"
              aria-describedby="password-requirements"
              value={formData['password'] || ''}
              onChange={handleChange}
            />
            <small id="password-requirements">
              At least 8 characters, including at least one number.
            </small>
          </div>
          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting ? 'Registering...' : 'Register'}
          </button>
        </form>
        {error && <p className="error-message">{error}</p>}
        <div className="form-footer">
          Already have an account? <Link to="/login">Login</Link>
        </div>
      </div>
    </div>
  );
}
