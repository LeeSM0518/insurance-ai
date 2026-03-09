/* eslint-disable react-refresh/only-export-components */
import React from 'react';
import { logError } from '@/lib/error-logger';
import { Button } from '@/components/ui/button';

interface Props {
  children: React.ReactNode;
}

interface State {
  hasError: boolean;
}

function ErrorFallback({ onReset }: { onReset: () => void }) {
  return (
    <div className="min-h-dvh flex flex-col items-center justify-center gap-4 px-6">
      <p className="text-destructive text-center">
        예기치 않은 오류가 발생했습니다.
      </p>
      <Button variant="outline" onClick={onReset}>
        다시 시도
      </Button>
    </div>
  );
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    logError({
      source: 'render',
      message: `React 렌더링 에러: ${error.message}`,
      error,
      context: { componentStack: errorInfo.componentStack },
    });
  }

  render() {
    if (this.state.hasError) {
      return <ErrorFallback onReset={() => this.setState({ hasError: false })} />;
    }
    return this.props.children;
  }
}
