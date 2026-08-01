import app from "./index.js";

const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`Mowell API listening on ${port}`));
