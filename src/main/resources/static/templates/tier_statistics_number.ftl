<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Document</title>
    <style>
        #tier_box {
            height: 800px;
            width: 1800px;
            background-color: #1b1a1a;
            overflow: hidden;
        }

        #tier_list {
            margin-top: 100px;
            width: 100%;
            display: flex;
            flex-direction: row;
            flex-wrap: wrap;
            align-content: space-around;
            justify-content: space-around;
        }

        .tier_img {
            padding-top: 10px;
            height: 200px;
            width: 150px;
        }

        #tier_cutoffs {
            margin-top: 30px;
            margin-left: 10px;
            display: flex;
            flex-direction: row;
            flex-wrap: wrap;

            justify-content: space-around;
        }

        .tier_item {
            font-size: 13px;
            font-weight: bold;
            color: #ffffff;
            height: 250px;
            width: 150px;
            text-align: center;
            border-radius: 21px;
            background: #35323257;
            box-shadow: inset 29px 29px 59px #38373702,
            inset -29px -29px 59px #33313163;
        }

        #tier_cutoffs .tier_item {

            height: 390px;
            width: 250px;
            font-size: 21px;
            line-height: 80px;
        }


        #tier_cutoffs .tier_img {
            height: 300px;
            width: 250px;
        }

        img {
            height: 100%;
            width: 100%;
        }
    </style>
</head>
<body>
<div id="tier_box">
    <div id="tier_cutoffs">
        <div class="tier_item">
            <div class="tier_img">
                <img src="/resources/images/tier/full/8.png" alt="">
            </div>
            ${eternal.mmr}
        </div>
        <div class="tier_item">
            <div class="tier_img">
                <img src="/resources/images/tier/full/7.png" alt="">
            </div>
            ${demigod.mmr}
        </div>
    </div>
    <div id="tier_list">
        <#list tierTypes as tierType>
            <div class="tier_item">
                <div class="tier_img">
                    <img src="/resources/images/tier/full/${tierType}.png" alt="">
                </div>
                ${count[tierType]!0}人(占比${rate[tierType]!"0"}%)
            </div>
        </#list>
    </div>
</div>
</body>

</html>