/**
 * ShopSphere App root — expected composition (wired up progressively):
 * - Routes: customer store (`/`), admin dashboard (`/admin`), auth pages
 * - Providers: QueryClientProvider (server state), AuthProvider (session)
 * Added now as true scaffolding placeholders that will be replaced in Phase 5.
 */
export default function App() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center justify-center gap-4 font-sans">
      <div className="rounded-2xl bg-slate-900 border border-slate-800 px-10 py-12 shadow-xl max-w-md text-center">
        <h1 className="text-3xl font-bold tracking-tight text-white">
          Shop<span className="text-indigo-400">Sphere</span>
        </h1>
        <p className="mt-3 text-slate-400 text-sm">
          Application scaffold initialized — backend on <code className="text-indigo-300">:8080</code>,
          frontend on <code className="text-indigo-300">:5173</code>. Phase 5 replaces this shell
          with the full storefront.
        </p>
      </div>
    </main>
  );
}
