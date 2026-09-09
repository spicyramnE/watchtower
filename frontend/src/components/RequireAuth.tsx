"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { NavBar } from "./NavBar";

/** Wraps a page: redirects to /login if not authenticated, otherwise renders the nav + page content. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace("/login");
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) {
    return null;
  }

  return (
    <>
      <NavBar />
      <main className="mx-auto max-w-5xl px-4 py-6">{children}</main>
    </>
  );
}
