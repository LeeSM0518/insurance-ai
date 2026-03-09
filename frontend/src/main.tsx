import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';
import { logError } from './lib/error-logger';

// 전역 동기 에러 캐치
window.onerror = (message, fileName, lineno, colno, error) => {
  logError({
    source: 'global',
    message: `Uncaught Error: ${String(message)}`,
    error,
    context: { fileName, lineno, colno },
  });
};

// 전역 Promise 에러 캐치
window.addEventListener('unhandledrejection', (event) => {
  logError({
    source: 'global',
    message: 'Unhandled Promise Rejection',
    error: event.reason,
  });
});

createRoot(document.getElementById('root')!).render(<App />);
