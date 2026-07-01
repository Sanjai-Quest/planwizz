import axios from 'axios';

let baseURL = import.meta.env.VITE_API_URL || 'http://localhost:8000/api';

// Render's `property: host` provides the raw service host URL (e.g. https://planwizz-backend.onrender.com)
// which lacks the "/api" context path prefix. Ensure it is appended if missing.
if (baseURL && !baseURL.endsWith('/api') && !baseURL.endsWith('/api/')) {
    baseURL = baseURL.endsWith('/') ? `${baseURL}api` : `${baseURL}/api`;
}

const api = axios.create({
    baseURL
});

export const uploadPDF = async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post('/upload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
    return response.data;
};

export const uploadText = async (text) => {
    const response = await api.post('/upload-text', { text });
    return response.data;
};

export const generateTimetable = async (preferences) => {
    const response = await api.post('/generate', preferences);
    return response.data;
};

export const checkCompatibility = async (preferences) => {
    const response = await api.post('/check-compatibility', preferences);
    return response.data;
};

export default api;
