import { Suspense } from 'react';
import { RouterProvider } from 'react-router';
import { Toaster } from '@/components/ui/sonner';
import { router } from '@/router';

function App() {
  return (
    <>
      <Suspense
        fallback={
          <div className="min-h-dvh flex items-center justify-center">
            <p className="text-muted-foreground">로딩 중...</p>
          </div>
        }
      >
        <RouterProvider router={router} />
      </Suspense>
      <Toaster position="top-center" richColors />
    </>
  );
}

export default App;
