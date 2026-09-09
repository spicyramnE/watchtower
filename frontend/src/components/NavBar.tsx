"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

export function NavBar() {
  const { username, role, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  function isActive(href: string) {
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  function linkClass(href: string) {
    return `px-3 py-2 rounded-md text-sm font-medium ${
      isActive(href) ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100"
    }`;
  }

  function handleLogout() {
    logout();
    router.push("/login");
  }

  return (
    <nav className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <div className="flex items-center gap-4">
          <span className="text-lg font-semibold text-slate-900">Watchtower</span>
          <Link href="/incidents" className={linkClass("/incidents")}>
            Incidents
          </Link>
          {role === "APPROVER" && (
            <Link href="/approvals" className={linkClass("/approvals")}>
              Approval Queue
            </Link>
          )}
        </div>
        <div className="flex items-center gap-3 text-sm text-slate-600">
          <span>
            {username} <span className="text-slate-400">·</span> {role}
          </span>
          <button
            onClick={handleLogout}
            className="rounded-md border border-slate-300 px-3 py-1 text-slate-700 hover:bg-slate-50"
          >
            Log out
          </button>
        </div>
      </div>
    </nav>
  );
}
