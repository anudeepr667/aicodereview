import { useState } from "react";
import { Code2, ScanSearch } from "lucide-react";
import Sidebar from "../components/Sidebar";

function QuickCodeScan() {
  const [code, setCode] = useState("");
  const [result, setResult] = useState(null);

  function scanCode() {
    const lines = code.split("\n");
    setResult({
      lines: code ? lines.length : 0,
      todos: (code.match(/TODO|FIXME/gi) || []).length,
      consoleCalls: (code.match(/console\.log\s*\(/g) || []).length,
      longLines: lines.filter((line) => line.length > 100).length,
    });
  }

  return (
    <div className="app-layout">
      <Sidebar />
      <main className="main-content">
        <div className="page-heading">
          <div>
            <span className="section-kicker">Local analysis</span>
            <h1>Quick Code Scan</h1>
            <p>Check a code snippet in your browser without calling the backend.</p>
          </div>
        </div>

        <div className="scan-grid">
          <section className="scan-card">
            <div className="scan-card-heading">
              <Code2 size={22} />
              <h2>Paste code</h2>
            </div>
            <textarea
              className="scan-input"
              value={code}
              onChange={(event) => setCode(event.target.value)}
              placeholder="Paste JavaScript, Java, or any code here..."
            />
            <button type="button" className="analyze-pr-btn" onClick={scanCode}>
              <ScanSearch size={18} />
              Scan snippet
            </button>
          </section>

          <section className="scan-card scan-result">
            {!result ? (
              <div className="empty-pr-preview">
                <ScanSearch size={42} />
                <h2>Ready to scan</h2>
                <p>Paste code and run a quick browser-only check.</p>
              </div>
            ) : (
              <>
                <h2>Scan results</h2>
                <div className="scan-metrics">
                  <strong>{result.lines}<span> lines</span></strong>
                  <strong>{result.todos}<span> TODO/FIXME</span></strong>
                  <strong>{result.consoleCalls}<span> console calls</span></strong>
                  <strong>{result.longLines}<span> long lines</span></strong>
                </div>
                <p className="scan-note">This lightweight scan is local-only and does not replace an AI pull-request review.</p>
              </>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}

export default QuickCodeScan;