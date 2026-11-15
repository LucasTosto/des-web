import { NavLink } from "react-router-dom";

const NavBar = () => {
  return (
    <nav className="border-b border-gray-200 bg-gray-100">
      <div className="mx-auto max-w-3xl px-4 py-4">
        <NavLink to="/" className="font-semibold text-gray-900">
          Tutoria Acadêmica com IA
        </NavLink>
      </div>
    </nav>
  );
};

export default NavBar;
