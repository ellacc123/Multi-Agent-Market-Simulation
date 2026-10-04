# Frontend

Run the dashboard locally:

```bash
cd frontend
npm install
npm run dev
```

Then open the local Vite URL, usually:

```text
http://localhost:5173
```

The app reads static simulation artifacts from:

```text
../artifacts/demo/
```

If those files are missing, the dashboard falls back to embedded sample data and shows a clear notice in the UI.
