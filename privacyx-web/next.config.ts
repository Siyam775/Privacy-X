import type { NextConfig } from "next";

const repositoryName = process.env.GITHUB_REPOSITORY?.split("/")[1];
const isUserOrOrganizationSite = repositoryName?.endsWith(".github.io") ?? false;
const githubPagesBasePath = process.env.NEXT_BASE_PATH ?? (
  process.env.GITHUB_ACTIONS === "true" && repositoryName && !isUserOrOrganizationSite
    ? `/${repositoryName}`
    : ""
);

const nextConfig: NextConfig = {
  output: "export",
  basePath: githubPagesBasePath,
  trailingSlash: true,
  images: { unoptimized: true },
};

export default nextConfig;
