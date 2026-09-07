import {
  BrowserRouter,
  Routes,
  Route,
} from "react-router-dom";

import Dashboard from "./pages/Dashboard";
import ReviewDetails from "./pages/ReviewDetails";
import NewReview from "./pages/NewReview";
import QuickCodeScan from "./pages/QuickCodeScan";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Dashboard />} />

        <Route
          path="/review/:id"
          element={<ReviewDetails />}
        />

        <Route
          path="/reviews/:id"
          element={<ReviewDetails />}
        />

        <Route
          path="/new-review"
          element={<NewReview />}
        />

        <Route
          path="/quick-scan"
          element={<QuickCodeScan />}
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;