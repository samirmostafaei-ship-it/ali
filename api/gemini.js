export default async function handler(req, res) {
  // تنظیم هدرهای CORS برای دسترسی بدون محدودیت
  res.setHeader('Access-Control-Allow-Credentials', true);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, x-goog-api-key, Authorization'
  );

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  try {
    const model = req.query.model || 'gemini-3.6-flash';
    const apiKey = req.query.key || req.headers['x-goog-api-key'] || process.env.GEMINI_API_KEY;

    if (!apiKey) {
      return res.status(400).json({
        error: { message: 'کلید API یافت نشد. لطفاً کلید را در هدر x-goog-api-key یا پارامتر ?key ارسال فرمایید.' }
      });
    }

    const cleanKey = apiKey.trim();
    const targetUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(cleanKey)}`;

    const response = await fetch(targetUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': cleanKey
      },
      body: typeof req.body === 'string' ? req.body : JSON.stringify(req.body)
    });

    const data = await response.json();
    return res.status(response.status).json(data);
  } catch (error) {
    console.error('Vercel Gemini Proxy Error:', error);
    return res.status(500).json({ error: { message: error.message || 'Internal proxy error' } });
  }
}
