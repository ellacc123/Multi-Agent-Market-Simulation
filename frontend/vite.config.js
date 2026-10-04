import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const frontendRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(frontendRoot, "..");
const artifactsRoot = path.resolve(repoRoot, "artifacts");

export default defineConfig({
  plugins: [
    react(),
    {
      name: "serve-nexus-artifacts",
      configureServer(server) {
        server.middlewares.use("/artifacts", (req, res, next) => {
          const requestPath = (req.url || "/").split("?")[0];
          const artifactPath = path.resolve(artifactsRoot, `.${requestPath}`);
          const relativePath = path.relative(artifactsRoot, artifactPath);
          if (relativePath === ".." || relativePath.startsWith(`..${path.sep}`) || path.isAbsolute(relativePath)) {
            res.statusCode = 403;
            res.end("Forbidden");
            return;
          }
          fs.stat(artifactPath, (error, stat) => {
            if (error || !stat.isFile()) {
              next();
              return;
            }
            fs.createReadStream(artifactPath).pipe(res);
          });
        });
      },
    },
  ],
  server: {
    fs: {
      allow: [frontendRoot],
    },
  },
});
