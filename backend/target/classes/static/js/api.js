window.apiClient = {
  request(path, options = {}) {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    return fetch("/api" + path, { ...options, headers });
  },

  importExcel(file) {
    const fd = new FormData();
    fd.append("file", file.raw);
    return fetch("/api/import/excel", { method: "POST", body: fd });
  }
};
