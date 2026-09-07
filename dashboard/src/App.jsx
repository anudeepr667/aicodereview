import {
  BrowserRouter,
  Routes,
  Route,
} from "react-router-dom";

import Dashboard from "./pages/Dashboard";
import ReviewDetails from "./pages/ReviewDetails";
import NewReview from "./pages/NewReview";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Dashboard />} />

        <Route
          path="/reviews/:id"
          element={<ReviewDetails />}
        />

        <Route
          path="/new-review"
          element={<NewReview />}
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;