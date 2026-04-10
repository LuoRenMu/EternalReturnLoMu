<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Document</title>
    <style>
        :root {
            --bg-color: #1c1b20;
            --card-bg: rgba(255, 255, 255, 0.05);
            --card-shadow: rgba(0, 0, 0, 0.2);
            --highlight: #ffcc00;
            font-family: 'Inter', '微软雅黑', sans-serif;
            color: #fff;
        }

        h2 {
            text-align: center;
            font-size: 2rem;
            font-weight: 600;
            margin-bottom: 20px;
            color: #9c9ba1
        }

        body {
            margin: 0;
            background: linear-gradient(135deg, #2b2a33, #1c1b20);
            display: flex;
            justify-content: center;
            align-items: flex-start;
            min-height: 100vh;
            padding: 40px;
        }

        h1 {
            text-align: center;
            font-size: 3rem;
            font-weight: 800;
            margin-bottom: 40px;
            text-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
        }


        #tier_box {
            padding: 30px;
            width: 1300px;
            background: var(--bg-color);
            border-radius: 50px;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
        }

        #tier_cutoffs {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: 30px;
            margin-bottom: 60px;
        }

        .tier_item {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: flex-start;
            background: var(--card-bg);
            border-radius: 20px;
            padding: 20px;
            width: 250px;
            height: 350px;
            font-size: 30px;
            font-weight: 800;
            line-height: 1.4;
            text-align: center;
            color: #fff;
            box-shadow:
                    0 8px 20px rgba(0, 0, 0, 0.3),
                    inset 0 0 10px rgba(255, 255, 255, 0.05);
            transition: transform 0.3s ease, box-shadow 0.3s ease;
        }



        .tier_img {
            width: 250px;
            height: 300px;
            margin-bottom: 20px;
            border-radius: 15px;
            overflow: hidden;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .tier_img img {
            width: 100%;
            height: 100%;
            object-fit: cover;

        }

        .tier_img img {
            transform: scale(1.05);
        }


        #tier_list {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: 20px;
        }

        #tier_list .tier_item {
            width: 180px;
            height: 180px;
            font-size: 16px;
            line-height: 1.4;
            padding: 15px;
        }

        #tier_list .tier_img {
            width: 100px;
            height: 150px;
        }

        #app {
            padding: 30px;
        }
    </style>
</head>
<body>
<div id="app">
    <div id="tier_box">
        <h1>${season}</h1>
        <h2>生成时间 ${date}</h2>
        <div id="tier_cutoffs">
            <div class="tier_item">
                <div class="tier_img">
                    <img src="${httpServer}/resources/images/tier/full/8.png" alt="">
                </div>
                ${eternal.mmr}
            </div>
            <div class="tier_item">
                <div class="tier_img">
                    <img src="${httpServer}/resources/images/tier/full/7.png" alt="">
                </div>
                ${demigod.mmr}
            </div>
        </div>
        <div id="tier_list">
            <#list tierTypes as tierType>
                <div class="tier_item">
                    <div class="tier_img">
                        <img src="${httpServer}/resources/images/tier/full/${tierType}.png" alt="">
                    </div>
                    ${count[tierType]!0}人(占比${rate[tierType]!"0"}%)
                </div>
            </#list>
        </div>
    </div>
</div>
</body>

</html>