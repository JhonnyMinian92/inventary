import { AuthProvider } from './AuthProvider.jsx';

export default function AppProviders({ children }) {
  return <AuthProvider>{children}</AuthProvider>;
}
